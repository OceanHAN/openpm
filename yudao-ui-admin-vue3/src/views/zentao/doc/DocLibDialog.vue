<template>
  <!-- 文档库管理：跟随产品/项目/执行的主库 + 团队空间下的自定义库 -->
  <el-dialog v-model="visible" title="文档库" width="720px" append-to-body>
    <el-alert
      type="info"
      :closable="false"
      show-icon
      class="mb-10px"
      title="跟随产品/项目/执行的库是「主库」，不允许删除"
      description="自定义库必须挂在团队空间下。本实现额外做了「库内还有文档就不能删」的保护（禅道原版会把文档留成孤儿）。"
    />
    <el-table :data="libs" border size="small">
      <el-table-column label="名称" prop="name" min-width="150" show-overflow-tooltip />
      <el-table-column label="类型" prop="typeName" width="120" align="center" />
      <el-table-column label="归属" width="140" align="center">
        <template #default="{ row }">
          <span v-if="row.type === 'product'">产品 #{{ row.product }}</span>
          <span v-else-if="row.type === 'project'">项目 #{{ row.project }}</span>
          <span v-else-if="row.type === 'execution'">执行 #{{ row.execution }}</span>
          <span v-else>空间 #{{ row.parent || '-' }}</span>
        </template>
      </el-table-column>
      <el-table-column label="文档数" prop="docCount" width="80" align="center" />
      <el-table-column label="主库" width="70" align="center">
        <template #default="{ row }">
          <el-tag v-if="row.main" type="success" size="small">主库</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="90" align="center">
        <template #default="{ row }">
          <el-button
            link
            type="danger"
            :disabled="row.main"
            @click="handleDelete(row)"
            v-hasPermi="['zentao:doc:delete']"
          >
            删除
          </el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-empty v-if="libs.length === 0" description="还没有文档库" />

    <template #footer>
      <el-button @click="visible = false">关闭</el-button>
    </template>
  </el-dialog>
</template>

<script lang="ts" setup>
import * as DocApi from '@/api/zentao/doc'

defineOptions({ name: 'ZentaoDocLibDialog' })

const emit = defineEmits(['success'])
const message = useMessage()
const visible = ref(false)
const libs = ref<DocApi.DocLibVO[]>([])

const open = (list: DocApi.DocLibVO[]) => {
  libs.value = list
  visible.value = true
}
defineExpose({ open })

const handleDelete = async (row: DocApi.DocLibVO) => {
  await message.delConfirm(`确认删除文档库「${row.name}」？库内还有文档时会被拒绝`)
  await DocApi.deleteDocLib(row.id!)
  message.success('删除成功')
  // 从本地列表里摘掉，父页面会重新拉一遍
  libs.value = libs.value.filter((l) => l.id !== row.id)
  emit('success')
}
</script>
