<template>
  <Dialog v-model="dialogVisible" :title="dialogTitle" width="720">
    <el-alert
      type="info"
      :closable="false"
      show-icon
      class="mb-15px"
      title="发布的三个禅道特色"
      description="① 版本号是全局唯一的（禅道按 system 查重，未启用系统时等价于全局）；② 创建发布时会自动生成一个同名的「影子构建」；③ 选了构建会把构建里完成的需求与解决的 Bug 同步进来。"
    />
    <el-form ref="formRef" v-loading="formLoading" :model="formData" :rules="formRules" label-width="120px">
      <el-row>
        <el-col :span="12">
          <el-form-item label="所属产品" prop="product">
            <el-select v-model="formData.product" placeholder="请选择产品" filterable class="w-full"
                       :disabled="formType === 'update'" @change="handleProductChange">
              <el-option v-for="p in productList" :key="p.id" :label="p.name" :value="p.id!" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item v-if="branchVisible" :label="branchLabel" prop="branches">
            <el-select v-model="formData.branches" multiple collapse-tags placeholder="请选择" class="w-full">
              <el-option label="主干" :value="0" />
              <el-option v-for="b in branchList" :key="b.id" :label="b.name" :value="b.id!" />
            </el-select>
          </el-form-item>
        </el-col>
      </el-row>

      <el-row>
        <el-col :span="12">
          <el-form-item label="版本号" prop="name">
            <el-input v-model="formData.name" placeholder="如 V1.0（全局唯一）" maxlength="255" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="状态" prop="status">
            <el-select v-model="formData.status" class="w-full">
              <el-option v-for="o in RELEASE_STATUS_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
            </el-select>
          </el-form-item>
        </el-col>
      </el-row>

      <el-form-item label="包含构建" prop="builds">
        <el-select v-model="formData.builds" multiple filterable placeholder="选择要发布的构建" class="w-full">
          <el-option v-for="b in buildList" :key="b.id" :label="`${b.name} (${b.date})`" :value="b.id!" />
        </el-select>
      </el-form-item>

      <el-form-item label="同步构建数据" prop="syncFromBuilds">
        <el-switch v-model="formData.syncFromBuilds" />
        <span class="ml-12px text-12px text-gray-500">
          开启后，构建（含集成构建的子构建）里完成的需求与解决的 Bug 会自动并进发布
        </span>
      </el-form-item>

      <el-row>
        <el-col :span="12">
          <el-form-item label="计划发布日期" prop="date">
            <el-date-picker v-model="formData.date" type="date" value-format="YYYY-MM-DD" class="w-full" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="实际发布日期" prop="releasedDate">
            <el-date-picker v-model="formData.releasedDate" type="datetime"
                            value-format="YYYY-MM-DD HH:mm:ss" class="w-full" />
          </el-form-item>
        </el-col>
      </el-row>

      <el-form-item label="里程碑" prop="marker">
        <el-switch v-model="markerFlag" />
      </el-form-item>

      <el-form-item label="描述" prop="desc">
        <el-input v-model="formData.desc" type="textarea" :rows="3" placeholder="请输入发布说明" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="submitForm" type="primary" :disabled="formLoading">确 定</el-button>
      <el-button @click="dialogVisible = false">取 消</el-button>
    </template>
  </Dialog>
</template>

<script lang="ts" setup>
import * as ReleaseApi from '@/api/zentao/release'
import * as ProductApi from '@/api/zentao/product'
import * as BranchApi from '@/api/zentao/branch'
import * as BuildApi from '@/api/zentao/build'
import { RELEASE_STATUS_OPTIONS } from './constants'

defineOptions({ name: 'ZentaoReleaseForm' })

const message = useMessage()
const { t } = useI18n()
const dialogVisible = ref(false)
const dialogTitle = ref('')
const formLoading = ref(false)
const formType = ref('')
const formRef = ref()
const productList = ref<ProductApi.ProductSimpleVO[]>([])
const branchList = ref<BranchApi.BranchVO[]>([])
const buildList = ref<BuildApi.BuildVO[]>([])

const formData = ref<ReleaseApi.ReleaseVO>({})
const markerFlag = computed({
  get: () => formData.value.marker === 1,
  set: (v: boolean) => (formData.value.marker = v ? 1 : 0)
})

const currentProduct = computed(() => productList.value.find((p) => p.id === formData.value.product))
const branchVisible = computed(
  () => currentProduct.value?.type === 'branch' || currentProduct.value?.type === 'platform'
)
const branchLabel = computed(() => (currentProduct.value?.type === 'platform' ? '平台' : '分支'))

const formRules = reactive({
  product: [{ required: true, message: '所属产品不能为空', trigger: 'change' }],
  name: [{ required: true, message: '版本号不能为空', trigger: 'blur' }],
  status: [{ required: true, message: '状态不能为空', trigger: 'change' }],
  date: [
    {
      validator: (_r: any, _v: any, cb: any) =>
        formData.value.status === 'normal' || formData.value.date ? cb() : cb(new Error('计划发布日期不能为空')),
      trigger: 'change'
    }
  ],
  releasedDate: [
    {
      validator: (_r: any, _v: any, cb: any) =>
        formData.value.status !== 'normal' || formData.value.releasedDate
          ? cb()
          : cb(new Error('已发布状态必须填写实际发布日期')),
      trigger: 'change'
    }
  ]
})

const handleProductChange = async () => {
  formData.value.branches = branchVisible.value ? [] : [0]
  formData.value.builds = []
  await loadBranchAndBuilds()
}

const loadBranchAndBuilds = async () => {
  if (!formData.value.product) {
    branchList.value = []
    buildList.value = []
    return
  }
  branchList.value = branchVisible.value
    ? await BranchApi.getBranchListByProduct(formData.value.product)
    : []
  buildList.value = await BuildApi.getBuildListByProduct(formData.value.product)
}

const open = async (type: string, id?: number, productId?: number) => {
  dialogVisible.value = true
  formType.value = type
  dialogTitle.value = type === 'create' ? '新建发布' : '编辑发布'
  resetForm()
  productList.value = await ProductApi.getProductSimpleList()
  if (id) {
    formLoading.value = true
    try {
      const data = await ReleaseApi.getRelease(id)
      formData.value = {
        ...data,
        branches: (data.branch || '0').split(',').filter(Boolean).map((x) => Number(x.trim())),
        builds: (data.build || '').split(',').filter(Boolean).map((x) => Number(x.trim()))
      }
      await loadBranchAndBuilds()
    } finally {
      formLoading.value = false
    }
  } else if (productId) {
    formData.value.product = productId
    await loadBranchAndBuilds()
  }
}
defineExpose({ open })

const emit = defineEmits(['success'])
const submitForm = async () => {
  if (!formRef.value) return
  await formRef.value.validate()
  formLoading.value = true
  try {
    const payload = { ...formData.value }
    if (formType.value === 'create') {
      await ReleaseApi.createRelease(payload)
      message.success(t('common.createSuccess'))
    } else {
      await ReleaseApi.updateRelease(payload)
      message.success(t('common.updateSuccess'))
    }
    dialogVisible.value = false
    emit('success')
  } finally {
    formLoading.value = false
  }
}

const resetForm = () => {
  formData.value = {
    product: undefined,
    branches: [0],
    builds: [],
    syncFromBuilds: true,
    name: '',
    status: 'wait',
    marker: 0,
    desc: ''
  }
  branchList.value = []
  buildList.value = []
  formRef.value?.resetFields()
}
</script>
