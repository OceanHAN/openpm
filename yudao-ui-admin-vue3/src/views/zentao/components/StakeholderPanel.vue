<template>
  <el-drawer v-model="visible" :title="`干系人：${title}`" size="760px" append-to-body>
    <div v-loading="loading">
      <el-alert
        type="info"
        :closable="false"
        show-icon
        class="mb-15px"
        title="干系人不是团队成员"
        description="团队成员（zt_team）是要干活的人，有角色和可用工时；干系人（zt_stakeholder）是需要知情或被影响的人 —— 甲方、领导、外部顾问都算。一个人可以只是干系人，不进团队；同一对象下也不能重复添加。"
      />

      <div class="mb-10px">
        <el-button type="primary" plain @click="openAdd" v-hasPermi="['zentao:stakeholder:update']">
          <Icon icon="ep:plus" class="mr-5px" /> 添加干系人
        </el-button>
        <span class="ml-10px text-gray-500">
          共 {{ members.length }} 人，其中关键干系人 <strong>{{ keyCount }}</strong> 人
        </span>
      </div>

      <el-table :data="members" border size="small" empty-text="还没有干系人">
        <el-table-column label="干系人" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">
            <el-tag v-if="row.key === 1" type="danger" size="small" class="mr-5px">关键</el-tag>
            {{ row.realname || row.user }}
            <span v-if="row.realname && row.realname !== row.user" class="text-gray-400">（{{ row.user }}）</span>
          </template>
        </el-table-column>
        <el-table-column label="内部/外部" align="center" width="100">
          <template #default="{ row }">
            <el-tag :type="row.type === 'outside' ? 'warning' : 'info'" size="small">{{ row.typeName }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="来源" align="center" width="110">
          <template #default="{ row }">
            <el-select v-model="row.from" size="small" class="!w-100px" @change="saveMember(row)">
              <el-option v-for="o in STAKEHOLDER_FROM_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="关键" align="center" width="80">
          <template #default="{ row }">
            <el-switch v-model="row.key" :active-value="1" :inactive-value="0" @change="saveMember(row)" />
          </template>
        </el-table-column>
        <el-table-column label="操作" align="center" width="80" fixed="right">
          <template #default="{ row }">
            <el-button link type="danger" @click="handleRemove(row)"
                       v-hasPermi="['zentao:stakeholder:update']">移除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <el-dialog v-model="addVisible" title="添加干系人" width="480px" append-to-body>
      <el-form label-width="90px">
        <el-form-item label="来源">
          <el-select v-model="addForm.from" class="!w-200px">
            <el-option v-for="o in STAKEHOLDER_FROM_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
          <span class="ml-10px text-gray-500 text-12px">选「外部人员」时直接填名字</span>
        </el-form-item>
        <el-form-item label="干系人">
          <el-select v-if="addForm.from !== 'outside'" v-model="addForm.user" filterable allow-create
                     default-first-option placeholder="选择账号" class="!w-280px">
            <el-option v-for="u in userList" :key="u.account"
                       :label="`${u.realname || u.account}（${u.account}）`" :value="u.account!" />
          </el-select>
          <el-input v-else v-model="addForm.user" placeholder="外部人员名字，如：张三（甲方）" class="!w-280px" />
        </el-form-item>
        <el-form-item label="关键干系人">
          <el-switch v-model="addForm.key" :active-value="1" :inactive-value="0" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="addVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleAdd">添加</el-button>
      </template>
    </el-dialog>
  </el-drawer>
</template>

<script lang="ts" setup>
import * as StakeholderApi from '@/api/zentao/stakeholder'
import * as OrgApi from '@/api/zentao/organization'
import { STAKEHOLDER_FROM_OPTIONS } from '@/api/zentao/stakeholder'

defineOptions({ name: 'ZentaoStakeholderPanel' })

const emit = defineEmits(['success'])
const message = useMessage()

const visible = ref(false)
const loading = ref(false)
const saving = ref(false)
const title = ref('')
const objectType = ref('project')
const objectID = ref<number>()
const members = ref<StakeholderApi.StakeholderVO[]>([])
const userList = ref<OrgApi.OrgUserVO[]>([])
const keyCount = computed(() => members.value.filter((m) => m.key === 1).length)

const addVisible = ref(false)
const addForm = reactive<{ from: string; user: string; key: number }>({ from: 'company', user: '', key: 0 })

const load = async () => {
  if (!objectID.value) return
  loading.value = true
  try {
    members.value = await StakeholderApi.getStakeholderList(objectType.value, objectID.value)
    // 关键干系人排前面（后端已经排好，这里保持原序）
  } finally {
    loading.value = false
  }
}

/**
 * 打开某对象（项目集/项目）的干系人
 * @param row  {id, name}
 * @param kind 'program' | 'project'
 */
const open = async (row: { id?: number; name?: string }, kind = 'project') => {
  objectID.value = row.id
  objectType.value = kind
  title.value = row.name || `#${row.id}`
  visible.value = true
  if (!userList.value.length) {
    try {
      userList.value = await OrgApi.getUserList({})
    } catch {
      userList.value = []
    }
  }
  await load()
}

const openAdd = () => {
  Object.assign(addForm, { from: 'company', user: '', key: 0 })
  addVisible.value = true
}

const handleAdd = async () => {
  if (!addForm.user) {
    message.warning('请选择或填写干系人')
    return
  }
  saving.value = true
  try {
    await StakeholderApi.createStakeholder({
      objectType: objectType.value,
      objectID: objectID.value,
      user: addForm.user,
      from: addForm.from,
      key: addForm.key
    })
    message.success('干系人已添加')
    addVisible.value = false
    await load()
    emit('success')
  } finally {
    saving.value = false
  }
}

const saveMember = async (row: StakeholderApi.StakeholderVO) => {
  await StakeholderApi.updateStakeholder({
    id: row.id,
    objectType: objectType.value,
    objectID: objectID.value,
    user: row.user,
    from: row.from,
    key: row.key
  })
  message.success('已保存')
  await load()
  emit('success')
}

const handleRemove = async (row: StakeholderApi.StakeholderVO) => {
  await message.delConfirm(`确认把「${row.realname || row.user}」移出干系人？`)
  await StakeholderApi.deleteStakeholder(row.id!)
  message.success('已移除')
  await load()
  emit('success')
}

defineExpose({ open })
</script>
