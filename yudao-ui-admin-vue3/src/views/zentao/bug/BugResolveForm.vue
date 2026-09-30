<template>
  <Dialog v-model="dialogVisible" title="解决缺陷" width="560">
    <el-alert
      type="info"
      :closable="false"
      show-icon
      class="mb-15px"
      title="禅道的两条联动校验"
      description="解决方案选「重复Bug」时必须指定重复的缺陷编号；选「已解决」时必须填写解决版本。"
    />
    <el-form ref="formRef" :model="formData" :rules="formRules" label-width="100px">
      <el-form-item label="解决方案" prop="resolution">
        <el-select v-model="formData.resolution" placeholder="请选择" class="w-full">
          <el-option v-for="o in BUG_RESOLUTION_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
        </el-select>
      </el-form-item>

      <el-form-item v-if="formData.resolution === 'fixed'" label="解决版本" prop="resolvedBuild">
        <el-select v-model="formData.resolvedBuild" placeholder="请选择构建（解决版本）" clearable filterable class="w-full">
          <el-option v-for="b in buildList" :key="b.id" :label="`${b.name} (${b.date})`" :value="String(b.id)" />
        </el-select>
        <div v-if="buildList.length === 0" class="text-12px text-gray-400">
          该产品还没有构建，可到「构建管理」先创建；也可以手工填写版本号
        </div>
      </el-form-item>

      <el-form-item v-if="formData.resolution === 'duplicate'" label="重复缺陷" prop="duplicateBug">
        <el-input-number v-model="formData.duplicateBug" :min="1" :controls="false"
                         placeholder="重复的缺陷编号" class="w-full" />
      </el-form-item>

      <el-form-item label="解决说明" prop="comment">
        <el-input v-model="formData.comment" type="textarea" :rows="3" placeholder="可选" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="submitForm" type="primary">提 交</el-button>
      <el-button @click="dialogVisible = false">取 消</el-button>
    </template>
  </Dialog>
</template>

<script lang="ts" setup>
import * as BugApi from '@/api/zentao/bug'
import * as BuildApi from '@/api/zentao/build'
import { BUG_RESOLUTION_OPTIONS } from './constants'

defineOptions({ name: 'ZentaoBugResolveForm' })

const message = useMessage()
const dialogVisible = ref(false)
const formRef = ref()
const formData = ref<BugApi.BugResolveVO>({ id: 0, resolution: 'fixed', resolvedBuild: '', comment: '' })

const formRules = reactive({
  resolution: [{ required: true, message: '解决方案不能为空', trigger: 'change' }],
  resolvedBuild: [
    {
      validator: (_r: any, _v: any, cb: any) => {
        if (formData.value.resolution === 'fixed' && !formData.value.resolvedBuild) {
          cb(new Error('解决方案为「已解决」时必须填写解决版本'))
        } else cb()
      },
      trigger: 'blur'
    }
  ],
  duplicateBug: [
    {
      validator: (_r: any, _v: any, cb: any) => {
        if (formData.value.resolution === 'duplicate' && !formData.value.duplicateBug) {
          cb(new Error('解决方案为「重复Bug」时必须填写缺陷编号'))
        } else cb()
      },
      trigger: 'blur'
    }
  ]
})

const buildList = ref<BuildApi.BuildVO[]>([])

const open = async (id: number, product?: number) => {
  dialogVisible.value = true
  formData.value = { id, resolution: 'fixed', resolvedBuild: '', duplicateBug: undefined, comment: '' }
  // 解决版本下拉 = 该产品的构建（zt_bug.resolvedBuild 存的就是构建编号）
  buildList.value = product ? await BuildApi.getBuildListByProduct(product).catch(() => []) : []
}
defineExpose({ open })

const emit = defineEmits(['success'])
const submitForm = async () => {
  if (!formRef.value) return
  await formRef.value.validate()
  await BugApi.resolveBug(formData.value)
  message.success('缺陷已解决')
  dialogVisible.value = false
  emit('success')
}
</script>
